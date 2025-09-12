#version 410 core

uniform vec2 iResolution;
uniform float iTime;
uniform vec2 iMouse;


float sqr(float x)
{
  return x * x;
}

float bumps(float x)
{
  float slider = iMouse.y / iResolution.y;
  float nozzle = 0.2;
  float pressure = pow(0.001, slider);
  float scaling = 0.1;
  float limit = scaling * sqrt(1.0 / pressure);
  if (nozzle < limit) {
    float equal_pressure = sqr(scaling / nozzle);
    float max_cone = 1.5;
    float factor = max_cone * (equal_pressure - pressure) / equal_pressure;
    float derivative = factor * 2.0 * iResolution.x / iResolution.y;
    float c = exp(-derivative / limit);
    float start = log((limit - nozzle) / limit) / log(c);
    return limit - limit * pow(c, start + x);
  } else {
    float omega = 100.0 * (nozzle - limit);
    float bumps = (nozzle - limit) * abs(cos(x * omega));
    return limit + bumps;
  };
}

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
  float fov = 5.0;
  vec2 uv = vec2(fragCoord.x / iResolution.x, 2.0 * fragCoord.y / iResolution.y - 1.0) * fov;
  float radius = bumps(uv.x);
  bool inside = abs(uv.y) <= radius;
  vec3 color;
  if (inside) {
    float a = radius * radius;
    float d = sqrt(radius * radius - uv.y * uv.y);
    color = 0.1 * vec3(1, 1, 1) * d / a;
  } else {
    color = vec3(0, 0, 0);
  };
  fragColor =  vec4(color, 1);
}
