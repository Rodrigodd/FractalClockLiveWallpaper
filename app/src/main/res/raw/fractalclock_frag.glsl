#version 100

precision lowp float;
varying lowp vec3 vColor;

void main() {
    gl_FragColor = vec4(vColor,1.0);
}