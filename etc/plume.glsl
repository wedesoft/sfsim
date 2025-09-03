#version 410 core

uniform vec2 iResolution;
uniform float iTime;
uniform vec2 iMouse;

float parabola(float x)
{
  return 0.2 / (0.1 + exp(-x * 10));
}

float bumps(float x)
{
  float omega = 20.0 * pow(0.1, iMouse.y / iResolution.y);
  return 0.1 + abs(sin(x * omega)) * 0.05;
}

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
  vec2 uv = vec2(fragCoord.x / iResolution.x, 2.0 * fragCoord.y / iResolution.y - 1.0);
  bool inside = abs(uv.y) <= mix(bumps(uv.x), parabola(uv.x), iMouse.y / iResolution.y);
  vec3 color = inside ? vec3(1, 1, 1) : vec3(0, 0, 0);
  fragColor =  vec4(color, 1);
}
