using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.HighDefinition;
using Crest;

/// <summary>
/// Configures Crest Ocean for photorealistic ocean rendering.
/// Attach to the OceanRenderer GameObject alongside the Crest OceanRenderer component.
/// </summary>
[RequireComponent(typeof(OceanRenderer))]
public class RealisticOceanSetup : MonoBehaviour
{
    [Header("Wave Presets")]
    [SerializeField] private OceanPreset _preset = OceanPreset.OpenOcean;
    [SerializeField, Range(0f, 2f)] private float _waveScale = 1f;
    [SerializeField, Range(0f, 360f)] private float _windDirection = 45f;
    [SerializeField, Range(0f, 30f)] private float _windSpeed = 10f;

    [Header("Ocean Appearance")]
    [SerializeField] private Color _shallowColor     = new Color(0.08f, 0.42f, 0.48f, 1f);
    [SerializeField] private Color _deepColor        = new Color(0.01f, 0.10f, 0.22f, 1f);
    [SerializeField] private Color _scatterColor     = new Color(0.02f, 0.25f, 0.35f, 1f);
    [SerializeField] private Color _foamColor        = new Color(0.92f, 0.96f, 1.00f, 1f);
    [SerializeField, Range(0f, 100f)] private float _visibility     = 18f;
    [SerializeField, Range(0f, 1f)]   private float _roughness      = 0.08f;
    [SerializeField, Range(0f, 1f)]   private float _refractionStrength = 0.35f;

    [Header("Foam")]
    [SerializeField, Range(0f, 5f)]  private float _foamCoverage   = 0.35f;
    [SerializeField, Range(0f, 5f)]  private float _foamDensity    = 1.4f;
    [SerializeField, Range(0f, 2f)]  private float _whitecapThreshold = 0.6f;

    [Header("HDRP Settings")]
    [SerializeField] private Volume _postProcessVolume;
    [SerializeField] private Light  _sunLight;

    private OceanRenderer _ocean;
    private Material _oceanMaterial;
    static readonly int s_ShallowColor   = Shader.PropertyToID("_ShallowColor");
    static readonly int s_DeepColor      = Shader.PropertyToID("_DeepColor");
    static readonly int s_ScatterColor   = Shader.PropertyToID("_ScatterColor");
    static readonly int s_FoamColor      = Shader.PropertyToID("_FoamColor");
    static readonly int s_Visibility     = Shader.PropertyToID("_Visibility");
    static readonly int s_Roughness      = Shader.PropertyToID("_Roughness");
    static readonly int s_RefractionStr  = Shader.PropertyToID("_RefractionStrength");
    static readonly int s_FoamCoverage   = Shader.PropertyToID("_FoamCoverage");
    static readonly int s_FoamDensity    = Shader.PropertyToID("_FoamDensity");
    static readonly int s_WhitecapThresh = Shader.PropertyToID("_WhitecapThreshold");

    private void Awake()
    {
        _ocean = GetComponent<OceanRenderer>();
        _oceanMaterial = _ocean.OceanMaterial;
    }

    private void Start()
    {
        ApplyPreset(_preset);
        ApplyMaterialSettings();
        SetupSunLight();
    }

    private void ApplyPreset(OceanPreset preset)
    {
        switch (preset)
        {
            case OceanPreset.Calm:
                _windSpeed = 4f; _waveScale = 0.4f; _foamCoverage = 0.05f;
                _roughness = 0.04f; _visibility = 30f;
                break;
            case OceanPreset.OpenOcean:
                _windSpeed = 10f; _waveScale = 1f; _foamCoverage = 0.35f;
                _roughness = 0.08f; _visibility = 18f;
                break;
            case OceanPreset.Storm:
                _windSpeed = 22f; _waveScale = 2f; _foamCoverage = 1.2f;
                _roughness = 0.22f; _visibility = 6f;
                break;
        }

        var windWaves = FindFirstObjectByType<ShapeFFT>();
        if (windWaves != null)
        {
            windWaves._windSpeed         = _windSpeed;
            windWaves._windTurbulence    = _preset == OceanPreset.Storm ? 0.35f : 0.12f;
            windWaves._waveDirectionVariance = 60f;
        }
    }

    private void ApplyMaterialSettings()
    {
        if (_oceanMaterial == null) return;
        _oceanMaterial.SetColor(s_ShallowColor,   _shallowColor);
        _oceanMaterial.SetColor(s_DeepColor,      _deepColor);
        _oceanMaterial.SetColor(s_ScatterColor,   _scatterColor);
        _oceanMaterial.SetColor(s_FoamColor,      _foamColor);
        _oceanMaterial.SetFloat(s_Visibility,     _visibility);
        _oceanMaterial.SetFloat(s_Roughness,      _roughness);
        _oceanMaterial.SetFloat(s_RefractionStr,  _refractionStrength);
        _oceanMaterial.SetFloat(s_FoamCoverage,   _foamCoverage);
        _oceanMaterial.SetFloat(s_FoamDensity,    _foamDensity);
        _oceanMaterial.SetFloat(s_WhitecapThresh, _whitecapThreshold);
    }

    private void SetupSunLight()
    {
        if (_sunLight == null) return;
        var hdLight = _sunLight.GetComponent<HDAdditionalLightData>();
        if (hdLight == null) return;

        _sunLight.color       = new Color(1.0f, 0.96f, 0.88f);
        hdLight.intensity     = 80000f; // lux
        hdLight.angularDiameter = 0.53f;
        hdLight.EnableShadows(true);
        hdLight.SetShadowResolutionOverride(true);
        hdLight.SetShadowResolution(2048);
    }

    // Called by OceanWeatherSystem at runtime
    public void SetWindSpeed(float speed) { _windSpeed = speed; ApplyPreset(_preset); }
    public void SetPreset(OceanPreset p)  { _preset = p; ApplyPreset(p); ApplyMaterialSettings(); }

    public enum OceanPreset { Calm, OpenOcean, Storm }
}
