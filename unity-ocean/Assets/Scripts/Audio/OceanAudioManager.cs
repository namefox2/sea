using UnityEngine;
using Crest;

/// <summary>
/// Spatially-blended layered ocean audio.
/// Layers: deep swell, mid-frequency chop, high foam/spray, seagulls.
/// All volumes driven by wave height and wind speed.
/// </summary>
public class OceanAudioManager : MonoBehaviour
{
    [System.Serializable]
    public class AudioLayer
    {
        public AudioSource source;
        [Range(0f, 1f)] public float maxVolume = 1f;
        public AnimationCurve windCurve = AnimationCurve.Linear(0f, 0f, 30f, 1f);
    }

    [Header("Audio Layers")]
    [SerializeField] private AudioLayer _deepSwell;
    [SerializeField] private AudioLayer _chopWaves;
    [SerializeField] private AudioLayer _foamSurf;
    [SerializeField] private AudioLayer _windHiss;
    [SerializeField] private AudioLayer _seagulls;
    [SerializeField] private AudioLayer _rain;

    [Header("Underwater")]
    [SerializeField] private AudioSource _underwaterAmbience;
    [SerializeField] private AudioMixerGroup _underwaterMixer;

    [Header("Smoothing")]
    [SerializeField] private float _volumeSmoothing = 2f;

    private ShapeFFT _windWaves;
    private Camera   _cam;
    private float    _windSpeed;
    private bool     _isUnderwater;
    private AudioLowPassFilter _lowPassFilter;

    private void Start()
    {
        _windWaves = FindFirstObjectByType<ShapeFFT>();
        _cam       = Camera.main;

        if (_cam != null)
            _lowPassFilter = _cam.GetComponent<AudioLowPassFilter>();

        StartAllLayers();
    }

    private void StartAllLayers()
    {
        StartIfValid(_deepSwell?.source);
        StartIfValid(_chopWaves?.source);
        StartIfValid(_foamSurf?.source);
        StartIfValid(_windHiss?.source);
        StartIfValid(_seagulls?.source);
        if (_underwaterAmbience != null) _underwaterAmbience.Play();
    }

    private static void StartIfValid(AudioSource src)
    {
        if (src != null && !src.isPlaying) src.Play();
    }

    private void Update()
    {
        if (_windWaves != null) _windSpeed = _windWaves._windSpeed;

        // Check underwater state
        if (OceanRenderer.Instance != null && _cam != null)
        {
            float camY    = _cam.transform.position.y;
            _isUnderwater = camY < OceanRenderer.Instance.SeaLevel;
        }

        UpdateSurfaceLayers();
        UpdateUnderwaterAudio();
    }

    private void UpdateSurfaceLayers()
    {
        SetLayerVolume(_deepSwell, _windSpeed, 1f - (_isUnderwater ? 1f : 0f));
        SetLayerVolume(_chopWaves, _windSpeed, 1f - (_isUnderwater ? 1f : 0f));
        SetLayerVolume(_foamSurf,  _windSpeed, 1f - (_isUnderwater ? 0.9f : 0f));
        SetLayerVolume(_windHiss,  _windSpeed, 1f);

        // Seagulls only during calm to moderate in daytime
        float seagullFactor = Mathf.Clamp01(1f - (_windSpeed - 12f) / 8f);
        SetLayerVolume(_seagulls, _windSpeed, seagullFactor);
    }

    private void UpdateUnderwaterAudio()
    {
        if (_underwaterAmbience != null)
        {
            float targetVol = _isUnderwater ? 0.8f : 0f;
            _underwaterAmbience.volume = Mathf.Lerp(_underwaterAmbience.volume, targetVol, Time.deltaTime * _volumeSmoothing);
        }

        if (_lowPassFilter != null)
        {
            float targetCutoff = _isUnderwater ? 800f : 22000f;
            _lowPassFilter.cutoffFrequency = Mathf.Lerp(_lowPassFilter.cutoffFrequency, targetCutoff, Time.deltaTime * 4f);
        }
    }

    private void SetLayerVolume(AudioLayer layer, float wind, float multiplier)
    {
        if (layer?.source == null) return;
        float targetVol = layer.windCurve.Evaluate(wind) * layer.maxVolume * multiplier;
        layer.source.volume = Mathf.Lerp(layer.source.volume, targetVol, Time.deltaTime * _volumeSmoothing);
    }
}
