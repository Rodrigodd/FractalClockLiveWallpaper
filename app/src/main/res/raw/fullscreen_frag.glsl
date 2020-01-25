
uniform sampler2D s_texture;
uniform vec3 color;

varying vec2 uv;

void main() {
    float alpha = texture2D(s_texture, uv).a;
    if (alpha == 0.0) discard;
    gl_FragColor =  vec4(color, alpha);
}
