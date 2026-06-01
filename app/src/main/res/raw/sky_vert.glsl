attribute vec2 aPos;
varying   vec2 vUV;

void main() {
    // vUV: (0,0)=bottom-left  (1,1)=top-right
    vUV = aPos * 0.5 + 0.5;
    // Depth 0 — sky always behind ocean mesh (depth test ALWAYS during sky pass)
    gl_Position = vec4(aPos, 0.0, 1.0);
}
