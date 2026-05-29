using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.HighDefinition;

/// <summary>
/// Adds atmospheric horizon haze — the bright, slightly desaturated band
/// near the sea horizon that makes ocean photos look photorealistic.
/// Uses HDRP Fog + a custom quad mesh at the horizon plane.
/// </summary>
public class HorizonHazeRenderer : MonoBehaviour
{
    [Header("Horizon Haze")]
    [SerializeField] private Volume _atmosVolume;
    [SerializeField, Range(0f, 1f)]   private float _hazeIntensity   = 0.35f;
    [SerializeField, Range(100f, 5000f)] private float _hazeDistance = 600f;
    [SerializeField] private Color   _hazeColor = new Color(0.78f, 0.88f, 0.95f);

    [Header("Aerial Perspective")]
    [SerializeField, Range(0f, 0.1f)] private float _aerialDensity = 0.012f;

    private Fog _fog;
    private Camera _cam;

    private void Start()
    {
        _cam = Camera.main;
        if (_atmosVolume == null) return;
        _atmosVolume.profile.TryGet(out _fog);
        ApplyHaze();
    }

    private void ApplyHaze()
    {
        if (_fog == null) return;
        _fog.enabled.Override(true);
        _fog.albedo.Override(_hazeColor);
        _fog.meanFreePath.Override(_hazeDistance);
        _fog.baseHeight.Override(-200f);
        _fog.maximumHeight.Override(300f);
        _fog.enableVolumetricFog.Override(false); // perf: keep off unless HDRP path allows it
    }

    private void Update()
    {
        if (_fog == null || _cam == null) return;
        // Vary haze intensity slightly with time for shimmer effect
        float shimmer = 1f + Mathf.Sin(Time.time * 0.15f) * 0.04f;
        _fog.meanFreePath.Override(_hazeDistance * shimmer);
    }
}
