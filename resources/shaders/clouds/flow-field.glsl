#version 410 core

uniform float curl_scale;
uniform float prevailing;
uniform float whirl;

float octaves_north(vec3 idx);
float octaves_south(vec3 idx);

float spin(float y)
{
  float latitude = asin(y);
  return sin(latitude * 3);
}

float flow_field(vec3 point)
{
  float m = spin(point.y);
  vec3 idx = point / (2 * curl_scale);
  float w1 = octaves_north(idx) * whirl;
  float w2 = octaves_south(idx) * whirl;
  return (w1 + prevailing) * (1 + m) / 2 - (w2 + prevailing) * (1 - m) / 2;
}
