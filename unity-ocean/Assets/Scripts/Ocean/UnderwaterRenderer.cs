using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.HighDefinition;

/// <summary>
/// Controls underwater visual transition: fog color, caustics intensity,
/// depth-based color absorption and blur.  Attach to the camera GameObject.
/// </summary>
[RequireComponent(typeof(Camera))]
public class UnderwaterRenderer : MonoBehaviour
{
    [Header("References")]
    [SerializeField] private Volume _globalVolume;

    [Header("Underwater Colors")]
    [SerializeField] private Color _underwaterFogColor  = new Color(0.0f, 0.22f, 0.35f);
    [SerializeField] private float _underwaterFogDensity = 0.12f;
    [SerializeField] private Color _causticsTint         = new Color(0.6f, 0.9f, 1.0f);

    [Header("Transition")]
    [SerializeField] private float _transitionDepth = 0.3f; // metres below surface for full blend

    private Fog         _fog;
    private ColorAdjustments _colorAdj;
    private Vignette    _vignette;
    private Camera      _cam;
    private Transform   _oceanSurface;
    private float       _surfaceY;
    private bool        _wasUnderwater;

    private void Start()
    {
        _cam = GetComponent<Camera>();

        if (_globalVolume != null)
        {
            _globalVolume.profile.TryGet(out _fog);
            _globalVolume.profile.TryGet(out _colorAdj);
            _globalVolume.profile.TryGet(out _vignette);
        }

        // Find ocean surface height from Crest
        var ocean = Crest.OceanRenderer.Instance;
        if (ocean != null) _oceanSurface = ocean.transform;
    }

    private void Update()
    {
        if (Crest.OceanRenderer.Instance == null) return;
        _surfaceY = Crest.OceanRenderer.Instance.SeaLevel;

        float camY = _cam.transform.position.y;
        float depth = _surfaceY - camY; // positive = submerged
        float blend = Mathf.Clamp01(depth / _transitionDepth);

        bool isUnderwater = depth > 0f;

        if (isUnderwater != _wasUnderwater)
        {
            _wasUnderwater = isUnderwater;
            OnTransition(isUnderwater);
        }

        ApplyUnderwaterEffects(blend);
    }

    private void OnTransition(bool entered)
    {
        // Could trigger splash VFX, audio, etc.
    }

    private void ApplyUnderwaterEffects(float blend)
    {
        if (_fog != null)
        {
            _fog.enabled.Override(blend > 0.01f);
            _fog.color.Override(Color.Lerp(Color.clear, _underwaterFogColor, blend));
            _fog.meanFreePath.Override(Mathf.Lerp(1000f, 8f / _underwaterFogDensity, blend));
        }

        if (_colorAdj != null)
        {
            // Desaturate reds at depth (Beer-Lambert absorption)
            float saturation = Mathf.Lerp(0f, -40f, blend);
            _colorAdj.saturation.Override(saturation);

            // Cool color temperature underwater
            float tempShift = Mathf.Lerp(0f, -400f, blend);
            _colorAdj.colorFilter.Override(Color.Lerp(Color.white, _causticsTint, blend * 0.6f));
        }

        if (_vignette != null)
        {
            _vignette.intensity.Override(Mathf.Lerp(0f, 0.4f, blend));
            _vignette.color.Override(_underwaterFogColor);
        }
    }
}
