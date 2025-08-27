#version 410 core

uniform vec2 iResolution;
uniform float iTime;

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
  float x = fragCoord.x / iResolution.x;
  float y = fragCoord.y / iResolution.y - 0.5;
  float s = 0.2 + 0.1 * abs(sin(10 * x));
  float luminocity = max(0, 1 - abs(y) / s);
  vec3 color = vec3(luminocity);
  fragColor = vec4(color, 1);
}
