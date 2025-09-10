#version 410 core

uniform vec2 iResolution;
uniform float iTime;
uniform vec2 iMouse;

float parabola(float x)
{
  return 0.2 / (0.1 + exp(-x * 10));
}

float bumps2(float x)
{
  float transition = 3 * iMouse.y / iResolution.y;
  float omega = 20.0 * pow(0.1, transition);
  float decay = exp(-x * 3);
  float bell = 2 * transition * (1.0 - exp(-x * 2 / (transition + 0.1)));
  return 0.1 + 0.1 * abs(cos(x * omega)) * decay + bell;
}

float bumps(float x)
{
  float slider = iMouse.y / iResolution.y;
  float pressure = pow(0.001, slider);
  float limit = 0.1 * sqrt(1.0 / pressure);
  float derivative = 3;
  float c = exp(-derivative / limit);
  return limit - limit * pow(c, x);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
  float fov = 5.0;
  vec2 uv = vec2(fragCoord.x / iResolution.x, 2.0 * fragCoord.y / iResolution.y - 1.0) * fov;
  float radius = bumps(uv.x);
  bool inside = abs(uv.y) <= radius;
  vec3 color;
  if (inside) {
    // float a = radius * radius;
    // float d = sqrt(radius * radius - uv.y * uv.y);
    // color = 0.1 * vec3(1, 1, 1) * d / a;
    color = vec3(1, 0.5, 0.5);
  } else {
    color = vec3(0, 0, 0);
  };
  fragColor =  vec4(color, 1);
}
