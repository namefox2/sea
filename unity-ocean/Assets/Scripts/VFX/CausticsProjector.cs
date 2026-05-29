using UnityEngine;

/// <summary>
/// Projects animated caustic light patterns onto the seabed.
/// Uses a flipbook of caustic textures scrolled with UV animation.
/// Attach to a child of the directional sun light.
/// </summary>
public class CausticsProjector : MonoBehaviour
{
    [SerializeField] private Projector _projector;
    [SerializeField] private Texture2D[] _causticFrames;  // 16-frame flipbook
    [SerializeField] private float _fps             = 24f;
    [SerializeField] private float _scrollSpeedX    = 0.02f;
    [SerializeField] private float _scrollSpeedY    = 0.015f;
    [SerializeField, Range(0f, 1f)] private float _maxIntensity = 0.6f;

    [Header("Depth Falloff")]
    [SerializeField] private float _maxDepth        = 8f;   // metres
    [SerializeField] private float _shallowFadeDepth = 0.5f;

    private Material _projMat;
    private int      _frameIndex;
    private float    _frameTimer;

    static readonly int s_MainTex  = Shader.PropertyToID("_MainTex");
    static readonly int s_Offset   = Shader.PropertyToID("_Offset");
    static readonly int s_Alpha    = Shader.PropertyToID("_Alpha");

    private void Start()
    {
        if (_projector != null)
            _projMat = _projector.material;
    }

    private void Update()
    {
        if (_causticFrames == null || _causticFrames.Length == 0) return;

        // Advance frame
        _frameTimer += Time.deltaTime * _fps;
        if (_frameTimer >= 1f)
        {
            _frameTimer -= 1f;
            _frameIndex  = (_frameIndex + 1) % _causticFrames.Length;
        }

        if (_projMat != null)
        {
            _projMat.SetTexture(s_MainTex, _causticFrames[_frameIndex]);
            _projMat.SetVector(s_Offset, new Vector4(
                Time.time * _scrollSpeedX,
                Time.time * _scrollSpeedY, 0f, 0f));

            // Fade by depth
            float depth = Crest.OceanRenderer.Instance != null
                ? Crest.OceanRenderer.Instance.SeaLevel - transform.position.y
                : 0f;
            float alpha = Mathf.Clamp01(depth / _shallowFadeDepth) *
                          Mathf.Clamp01(1f - depth / _maxDepth) * _maxIntensity;
            _projMat.SetFloat(s_Alpha, alpha);
        }
    }
}
