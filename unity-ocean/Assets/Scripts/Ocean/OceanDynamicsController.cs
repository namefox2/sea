using UnityEngine;
using Crest;
using Unity.Mathematics;

/// <summary>
/// Runtime ocean dynamics: wind gusts, wave swell direction changes, diurnal tide.
/// Drives ShapeFFT and OceanDepthCache inputs every frame.
/// </summary>
public class OceanDynamicsController : MonoBehaviour
{
    [Header("Wind")]
    [SerializeField] private float _baseWindSpeed       = 10f;
    [SerializeField] private float _gustAmplitude       = 3f;
    [SerializeField] private float _gustPeriod          = 12f;  // seconds
    [SerializeField, Range(0f, 360f)] private float _windDir = 45f;
    [SerializeField] private float _windDirDriftSpeed   = 2f;   // deg/s max

    [Header("Swell")]
    [SerializeField] private float _swellHeight         = 1.5f;
    [SerializeField] private float _swellPeriod         = 14f;  // seconds
    [SerializeField, Range(0f, 360f)] private float _swellDirection = 220f;

    [Header("Tidal")]
    [SerializeField] private bool  _enableTide          = true;
    [SerializeField] private float _tidalAmplitude      = 0.4f; // metres
    [SerializeField] private float _tidalPeriod         = 44700f; // ~12.4h in seconds

    private ShapeFFT   _windWaves;
    private ShapeFFT   _swellWaves;
    private float      _noiseOffset;

    private void Awake()
    {
        // Assume two ShapeFFT components: first = wind chop, second = swell
        var ffts = FindObjectsByType<ShapeFFT>(FindObjectsSortMode.None);
        if (ffts.Length >= 1) _windWaves  = ffts[0];
        if (ffts.Length >= 2) _swellWaves = ffts[1];

        _noiseOffset = UnityEngine.Random.value * 100f;
    }

    private void Update()
    {
        float t = Time.time;

        UpdateWind(t);
        UpdateSwell(t);
        if (_enableTide) UpdateTide(t);
    }

    private void UpdateWind(float t)
    {
        if (_windWaves == null) return;

        // Perlin gust
        float gust = (Mathf.PerlinNoise(t / _gustPeriod, _noiseOffset) - 0.5f) * 2f * _gustAmplitude;
        _windWaves._windSpeed = Mathf.Max(0.5f, _baseWindSpeed + gust);

        // Slow direction drift
        float targetDir  = _windDir + Mathf.Sin(t * 0.03f) * 20f;
        float currentDir = _windWaves.transform.eulerAngles.y;
        float newDir     = Mathf.MoveTowardsAngle(currentDir, targetDir, _windDirDriftSpeed * Time.deltaTime);
        _windWaves.transform.rotation = Quaternion.Euler(0f, newDir, 0f);
    }

    private void UpdateSwell(float t)
    {
        if (_swellWaves == null) return;
        // Swell amplitude oscillates gently with a long-period noise
        float swellMod = 0.8f + 0.2f * Mathf.Sin(t / 120f);
        _swellWaves._windSpeed = _swellHeight * swellMod * 4f; // rough mapping
        _swellWaves.transform.rotation = Quaternion.Euler(0f, _swellDirection, 0f);
    }

    private void UpdateTide(float t)
    {
        if (OceanRenderer.Instance == null) return;
        float tidalOffset = _tidalAmplitude * Mathf.Sin((t / _tidalPeriod) * math.PI2);
        OceanRenderer.Instance.SeaLevel = tidalOffset;
    }
}
