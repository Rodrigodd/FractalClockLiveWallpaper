precision mediump float;

attribute vec4 vertex;

uniform vec2 screenSize;

varying mediump vec2 uv;

void main() {
    uv = vertex.zw;
    gl_Position = vec4(vertex.xy, 0.0, 1.0);
}
