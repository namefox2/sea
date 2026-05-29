using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.HighDefinition;
using System.Collections;

/// <summary>
/// Drives time-of-day, weather state transitions, and their effect on the ocean.
/// Works with HDRP PhysicallyBasedSky and a directional sun light.
/// </summary>
public class OceanWeatherSystem : MonoBehaviour
{
    public enum WeatherState { Clear, PartlyCloudy, Overcast, Storm }
    public enum TimeOfDay   { Dawn, Morning, Noon, Afternoon, Dusk, Night }

    [Header("Time")]
    [SerializeField] private bool  _autoAdvanceTime = true;
    [SerializeField] private float _dayLengthSeconds = 300f; // real-seconds per 24h
    [SerializeField, Range(0f, 24f)] private float _currentHour = 10f;

    [Header("Weather")]
    [SerializeField] private WeatherState _weatherState = WeatherState.Clear;
    [SerializeField] private float _transitionDuration  = 30f;

    [Header("References")]
    [SerializeField] private Light         _sun;
    [SerializeField] private Light         _moon;
    [SerializeField] private Volume        _skyVolume;
    [SerializeField] private RealisticOceanSetup _oceanSetup;
    [SerializeField] private AudioSource   _windAudio;
    [SerializeField] private AudioSource   _rainAudio;

    private PhysicallyBasedSky _pbSky;
    private Fog                _fog;
    private HDAdditionalLightData _sunHD;
    private HDAdditionalLightData _moonHD;
    private WeatherState _targetWeather;
    private float _weatherBlend = 1f;

    // Sun color keyframes by hour
    private static readonly (float hour, Color color, float intensity)[] SunKeyframes =
    {
        (5f,  new Color(1.0f, 0.50f, 0.20f), 2500f),   // dawn
        (7f,  new Color(1.0f, 0.85f, 0.65f), 20000f),  // early morning
        (10f, new Color(1.0f, 0.97f, 0.90f), 60000f),  // morning
        (13f, new Color(1.0f, 1.00f, 0.95f), 80000f),  // noon
        (17f, new Color(1.0f, 0.90f, 0.70f), 50000f),  // afternoon
        (19f, new Color(1.0f, 0.55f, 0.22f), 8000f),   // dusk
        (21f, new Color(0.2f, 0.20f, 0.35f), 500f),    // twilight
    };

    private void Awake()
    {
        if (_skyVolume != null)
        {
            _skyVolume.profile.TryGet(out _pbSky);
            _skyVolume.profile.TryGet(out _fog);
        }

        if (_sun  != null) _sunHD  = _sun.GetComponent<HDAdditionalLightData>();
        if (_moon != null) _moonHD = _moon.GetComponent<HDAdditionalLightData>();

        _targetWeather = _weatherState;
    }

    private void Update()
    {
        if (_autoAdvanceTime)
            _currentHour = (_currentHour + (24f / _dayLengthSeconds) * Time.deltaTime) % 24f;

        UpdateSunPosition();
        UpdateSunColor();
        UpdateWeatherEffects();
    }

    private void UpdateSunPosition()
    {
        // Simple azimuth/elevation from hour angle
        float hourAngle = (_currentHour / 24f) * 360f - 90f;
        float elevation = Mathf.Sin((_currentHour / 24f) * Mathf.PI * 2f - Mathf.PI / 2f) * 60f;

        if (_sun  != null) _sun.transform.rotation = Quaternion.Euler(elevation, hourAngle, 0f);
        if (_moon != null) _moon.transform.rotation = Quaternion.Euler(-elevation + 30f, hourAngle + 180f, 0f);

        bool isDay = _currentHour > 5.5f && _currentHour < 20.5f;
        if (_sun  != null) _sun.enabled  = isDay;
        if (_moon != null) _moon.enabled = !isDay;
    }

    private void UpdateSunColor()
    {
        if (_sun == null || _sunHD == null) return;

        // Find surrounding keyframes
        for (int i = 0; i < SunKeyframes.Length - 1; i++)
        {
            if (_currentHour >= SunKeyframes[i].hour && _currentHour < SunKeyframes[i + 1].hour)
            {
                float t = Mathf.InverseLerp(SunKeyframes[i].hour, SunKeyframes[i + 1].hour, _currentHour);
                _sun.color        = Color.Lerp(SunKeyframes[i].color, SunKeyframes[i + 1].color, t);
                _sunHD.intensity  = Mathf.Lerp(SunKeyframes[i].intensity, SunKeyframes[i + 1].intensity, t);
                break;
            }
        }
    }

    private void UpdateWeatherEffects()
    {
        if (_weatherState != _targetWeather)
        {
            _weatherBlend += Time.deltaTime / _transitionDuration;
            if (_weatherBlend >= 1f)
            {
                _weatherBlend  = 1f;
                _weatherState  = _targetWeather;
            }
        }

        float cloudiness = GetCloudiness(_weatherState);

        if (_pbSky != null)
            _pbSky.cloudOpacity.Override(cloudiness);

        // Fog density
        if (_fog != null)
        {
            float fogDensity = _weatherState switch
            {
                WeatherState.Clear        => 0.0f,
                WeatherState.PartlyCloudy => 0.005f,
                WeatherState.Overcast     => 0.02f,
                WeatherState.Storm        => 0.06f,
                _                         => 0.0f
            };
            _fog.enabled.Override(fogDensity > 0.001f);
            _fog.meanFreePath.Override(fogDensity > 0f ? 1f / fogDensity : 10000f);
        }

        // Audio
        if (_windAudio != null)
        {
            float windVol = _weatherState == WeatherState.Storm ? 0.9f :
                            _weatherState == WeatherState.Overcast ? 0.4f : 0.1f;
            _windAudio.volume = Mathf.Lerp(_windAudio.volume, windVol, Time.deltaTime);
        }

        if (_rainAudio != null)
        {
            float rainVol = _weatherState == WeatherState.Storm ? 0.7f : 0f;
            _rainAudio.volume = Mathf.Lerp(_rainAudio.volume, rainVol, Time.deltaTime);
        }

        // Drive ocean preset
        if (_oceanSetup != null)
        {
            var preset = _weatherState switch
            {
                WeatherState.Clear        => RealisticOceanSetup.OceanPreset.Calm,
                WeatherState.PartlyCloudy => RealisticOceanSetup.OceanPreset.OpenOcean,
                WeatherState.Overcast     => RealisticOceanSetup.OceanPreset.OpenOcean,
                WeatherState.Storm        => RealisticOceanSetup.OceanPreset.Storm,
                _                         => RealisticOceanSetup.OceanPreset.OpenOcean
            };
            _oceanSetup.SetPreset(preset);
        }
    }

    private static float GetCloudiness(WeatherState state) => state switch
    {
        WeatherState.Clear        => 0.0f,
        WeatherState.PartlyCloudy => 0.3f,
        WeatherState.Overcast     => 0.8f,
        WeatherState.Storm        => 1.0f,
        _                         => 0.0f
    };

    public void SetWeather(WeatherState state)
    {
        _targetWeather = state;
        _weatherBlend  = 0f;
    }

    public void SetTimeOfDay(float hour) => _currentHour = Mathf.Clamp(hour, 0f, 24f);
}
