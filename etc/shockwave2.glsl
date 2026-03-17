#version 450 core

#define M_PI 3.1415926535897932384626433832795
#define SHIP_COLOR vec3(0.7, 0.7, 0.8)
#define SIZE 0.1
#define THICKNESS 0.005
#define XPOS 0.6

uniform vec2 iResolution;
uniform float iTime;
uniform vec2 iMouse;

float angle()
{
  return (iMouse.x / iResolution.x - 0.5) * 45.0 / 180.0 * M_PI;
}

float line_sdf(vec2 p) {
  float a = angle();
  float cos_a = cos(a);
  float sin_a = sin(a);
  mat2 m = mat2(cos_a, sin_a, -sin_a, cos_a);
  vec2 ps = m * (p - vec2(XPOS, 0));
  vec2 q = vec2(0.0, clamp(ps.y, -SIZE, SIZE));
  return length(ps - q);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  vec2 uv = (fragCoord - iResolution.xy / 2.0) / (iResolution.x / 2.0);
  float sdf = line_sdf(uv);
  if (sdf < THICKNESS)
    fragColor = vec4(SHIP_COLOR, 1);
  else {
    fragColor = vec4(0, 0, 0, 1);
  };
}
