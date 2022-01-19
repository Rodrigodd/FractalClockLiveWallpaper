#version 100

attribute float vertexIDf;
uniform vec2 screenSize;
uniform float clockSize;
uniform vec2 time;

uniform lowp vec3 hourColor;
uniform lowp vec3 minColor;

const float PI = 3.141592653;

varying lowp vec3 vColor;

void main() {
    int deep = int(log2(floor(vertexIDf/2.0) + 1.0));
    int id = int(floor(vertexIDf/2.0) + 1.0 - exp2(float(deep)));
    deep += int(mod(vertexIDf, 2.0));

    vec2 pos = vec2(0.0, 0.0);
    float length = clockSize * min(screenSize.x, screenSize.y);
    float angle;

    if(int(mod(float(id), 2.0)) == 0) {
        // rotate left
        angle = 2.0 * PI * time.x;
        vColor = hourColor;
    } else {
        // rotate right
        angle = 2.0 * PI * time.y;
        vColor = minColor;
    }
    int i;
    for (i = 1; i < deep - 1; i++) {
        pos.x += length * sin(angle) / screenSize.x;
        pos.y += length * cos(angle) / screenSize.y;
        length = length*0.7;

        id = id / 2;
        if(int(mod(float(id), 2.0)) == 0) {
            // rotate left
            angle += 2.0 * PI * time.x;
        } else {
            // rotate right
            angle += 2.0 * PI * time.y;
        }
    }
    if (i<deep) {
        pos.x += length * sin(angle) / screenSize.x;
        pos.y += length * cos(angle) / screenSize.y;

        if (int(mod(float(id), 2.0)) == 0) {
            vColor = hourColor;
        } else {
            vColor = minColor;
        }
    }

    gl_Position = vec4(pos, 0.0, 1.0);
}