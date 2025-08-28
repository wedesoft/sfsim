#version 410 core

#define M_PI 3.1415926535897932384626433832795

uniform vec2 iResolution;
uniform float iTime;

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
  float x = fragCoord.x / iResolution.x;
  float y = 2 * fragCoord.y / iResolution.y - 1.0;
  float s = 0.4 + 0.15 * abs(sin(10 * x));
  float s2 = s * 0.8;
  float dy = abs(y) / s;
  float dx = sqrt(max(0, 1 - dy * dy));
  float dy2 = abs(y) / s2;
  float dx2 = sqrt(max(0, 1 - dy2 * dy2));
  float luminocity = 1.0 * dx - 0.8 * dx2;
  vec3 color = vec3(0.8, 0.8, 1.0) * luminocity;
  fragColor = vec4(color, 1);
}
