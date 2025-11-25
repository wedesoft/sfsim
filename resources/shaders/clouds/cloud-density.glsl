#version 450 core

uniform float cap;

float cloud_base(vec3 point);
float cloud_noise(vec3 point, float lod);
float remap(float value, float original_min, float original_max, float new_min, float new_max);

float cloud_density(vec3 point, float lod)
{
  float base = cloud_base(point);
  float result;
  if (base <= 0.0)
    result = base;
  else {
    float noise = cloud_noise(point, lod);
    if (noise <= 1.0 - base)
      result = 0.0;
    else
      result = remap(noise, 1.0 - base, 1.0, 0.0, cap);
  };
  return result;
}
