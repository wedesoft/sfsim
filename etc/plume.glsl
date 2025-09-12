#version 410 core

uniform vec2 iResolution;
uniform float iTime;
uniform vec2 iMouse;

#define nozzle 0.2
#define scaling 0.1
#define max_cone 1.5

float sqr(float x)
{
  return x * x;
}

float pressure()
{
  float slider = iMouse.y / iResolution.y;
  return pow(0.001, slider);
}

float limit(float pressure)
{
  return scaling * sqrt(1.0 / pressure);
}

float bumps(float x)
{
  float pressure = pressure();
  float limit = limit(pressure);
  if (nozzle < limit) {
    float equal_pressure = sqr(scaling / nozzle);
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
  float fov = 2.0;
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
