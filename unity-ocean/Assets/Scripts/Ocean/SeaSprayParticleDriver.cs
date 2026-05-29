using UnityEngine;
using Crest;

/// <summary>
/// Spawns sea spray / mist particles at wave crests based on current wind speed.
/// Place this on an empty GameObject near the camera.
/// Requires a ParticleSystem configured as a "spray" emitter.
/// </summary>
public class SeaSprayParticleDriver : MonoBehaviour
{
    [SerializeField] private ParticleSystem _sprayPS;
    [SerializeField] private ParticleSystem _mistPS;
    [SerializeField] private ParticleSystem _foamLinePS;

    [Header("Thresholds")]
    [SerializeField] private float _sprayWindThreshold = 8f;
    [SerializeField] private float _mistWindThreshold  = 4f;

    [Header("Emission Scaling")]
    [SerializeField] private float _sprayMaxRate  = 120f;
    [SerializeField] private float _mistMaxRate   = 60f;

    private ShapeFFT _windWaves;

    private void Start()
    {
        _windWaves = FindFirstObjectByType<ShapeFFT>();
    }

    private void Update()
    {
        if (_windWaves == null) return;
        float wind = _windWaves._windSpeed;

        SetEmission(_sprayPS,    wind, _sprayWindThreshold, _sprayMaxRate);
        SetEmission(_mistPS,     wind, _mistWindThreshold,  _mistMaxRate);

        // Foam lines increase with wave height
        if (_foamLinePS != null)
        {
            float foamFactor = Mathf.Clamp01((wind - 6f) / 10f);
            var em = _foamLinePS.emission;
            em.rateOverTime = foamFactor * 40f;
        }

        // Follow camera horizontally
        var cam = Camera.main;
        if (cam != null)
        {
            Vector3 p = cam.transform.position;
            p.y = OceanRenderer.Instance != null ? OceanRenderer.Instance.SeaLevel + 0.5f : p.y;
            transform.position = p;
        }
    }

    private static void SetEmission(ParticleSystem ps, float wind, float threshold, float maxRate)
    {
        if (ps == null) return;
        float t   = Mathf.Clamp01((wind - threshold) / (30f - threshold));
        var   em  = ps.emission;
        em.rateOverTime = t * maxRate;
    }
}
