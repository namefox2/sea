using UnityEngine;
using Crest;

/// <summary>
/// Cinematic camera that floats on the ocean surface with bob and roll.
/// Can switch between surface view, aerial, and underwater automatically.
/// Drives Cinemachine Brain via CameraState enum.
/// </summary>
public class CinematicOceanCamera : MonoBehaviour
{
    public enum CameraState { Surface, LowAerial, HighAerial, Underwater, BeachShore }

    [Header("Camera State")]
    [SerializeField] private CameraState _state = CameraState.Surface;

    [Header("Surface Bob Settings")]
    [SerializeField] private float _bobAmplitude   = 0.25f;
    [SerializeField] private float _bobFrequency   = 0.6f;
    [SerializeField] private float _rollAmplitude  = 1.5f;  // degrees
    [SerializeField] private float _rollFrequency  = 0.4f;
    [SerializeField] private float _bobSmoothing   = 5f;

    [Header("Heights")]
    [SerializeField] private float _surfaceOffset    =  1.2f;  // above sea level
    [SerializeField] private float _underwaterDepth  = -2.5f;
    [SerializeField] private float _lowAerialHeight  = 8f;
    [SerializeField] private float _highAerialHeight = 80f;

    [Header("Movement")]
    [SerializeField] private float _driftSpeed      = 0.4f;   // m/s slow drift
    [SerializeField] private float _driftNoiseScale = 0.03f;

    [Header("Look")]
    [SerializeField] private Vector2 _lookNoiseSpeed  = new Vector2(0.04f, 0.06f);
    [SerializeField] private Vector2 _lookNoiseRange  = new Vector2(4f, 2f);   // degrees

    private Vector3 _targetPosition;
    private Quaternion _targetRotation;
    private float _noiseOffset;
    private float _seaLevel;
    private SampleHeightHelper _heightSampler = new SampleHeightHelper();

    private void Awake()
    {
        _noiseOffset = Random.value * 1000f;
        _targetPosition = transform.position;
        _targetRotation = transform.rotation;
    }

    private void LateUpdate()
    {
        if (OceanRenderer.Instance == null) return;

        _seaLevel = OceanRenderer.Instance.SeaLevel;

        // Sample wave height at camera's XZ position
        _heightSampler.Init(transform.position, 1f, true);
        _heightSampler.Sample(out float waveHeight, out _, out _);

        switch (_state)
        {
            case CameraState.Surface:    UpdateSurface(waveHeight);    break;
            case CameraState.LowAerial:  UpdateAerial(_lowAerialHeight);  break;
            case CameraState.HighAerial: UpdateAerial(_highAerialHeight); break;
            case CameraState.Underwater: UpdateUnderwater();            break;
            case CameraState.BeachShore: UpdateShore(waveHeight);      break;
        }

        transform.position = Vector3.Lerp(transform.position, _targetPosition, Time.deltaTime * _bobSmoothing);
        transform.rotation = Quaternion.Slerp(transform.rotation, _targetRotation, Time.deltaTime * _bobSmoothing);
    }

    private void UpdateSurface(float waveHeight)
    {
        float t = Time.time;

        // Drift horizontally
        float dx = (Mathf.PerlinNoise(t * _driftNoiseScale, _noiseOffset)       - 0.5f) * _driftSpeed;
        float dz = (Mathf.PerlinNoise(_noiseOffset, t * _driftNoiseScale + 100f) - 0.5f) * _driftSpeed;
        _targetPosition = transform.position + new Vector3(dx, 0f, dz) * Time.deltaTime;

        // Bob on surface
        float bob  = Mathf.Sin(t * _bobFrequency   * Mathf.PI * 2f) * _bobAmplitude;
        _targetPosition.y = waveHeight + _surfaceOffset + bob;

        // Roll with waves
        float roll  = Mathf.Sin(t * _rollFrequency * Mathf.PI * 2f)           * _rollAmplitude;
        float pitch = Mathf.Sin(t * _rollFrequency * Mathf.PI * 2f + 1.2f)    * (_rollAmplitude * 0.5f);

        // Cinematic look noise
        float lookH = (Mathf.PerlinNoise(t * _lookNoiseSpeed.x, _noiseOffset + 50f) - 0.5f) * _lookNoiseRange.x;
        float lookV = (Mathf.PerlinNoise(_noiseOffset + 70f, t * _lookNoiseSpeed.y) - 0.5f) * _lookNoiseRange.y;

        _targetRotation = Quaternion.Euler(pitch + lookV, transform.eulerAngles.y + lookH * 0.1f, roll);
    }

    private void UpdateAerial(float targetHeight)
    {
        _targetPosition = new Vector3(transform.position.x, _seaLevel + targetHeight, transform.position.z);

        float t    = Time.time;
        float tilt = Mathf.Sin(t * 0.03f) * 5f; // slow aerial pan tilt
        _targetRotation = Quaternion.Euler(55f + tilt, transform.eulerAngles.y, 0f);
    }

    private void UpdateUnderwater()
    {
        _targetPosition   = new Vector3(transform.position.x, _seaLevel + _underwaterDepth, transform.position.z);
        float t = Time.time;
        float roll = Mathf.Sin(t * 0.3f) * 1.5f;
        _targetRotation = Quaternion.Euler(10f, transform.eulerAngles.y, roll);
    }

    private void UpdateShore(float waveHeight)
    {
        // Low wide angle view from shore level
        _targetPosition.y = waveHeight + 0.4f;
        _targetRotation   = Quaternion.Euler(5f, transform.eulerAngles.y, 0f);
    }

    public void SetState(CameraState state) => _state = state;
}
