#version 100

precision mediump float;

uniform sampler2D s_texture;
uniform lowp vec3 color;

varying vec2 uv;

void main() {
    lowp float alpha = texture2D(s_texture, uv).a;
    if (alpha == 0.0) discard;
    gl_FragColor =  vec4(color, alpha);
}
