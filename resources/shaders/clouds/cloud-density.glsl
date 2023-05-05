#version 410 core

uniform float cloud_multiplier;

float cloud_noise(vec3 point, float lod);
float cloud_profile(vec3 point);

float cloud_density(vec3 point, float lod)
{
  float noise = cloud_noise(point, lod);
  float profile = cloud_profile(point);
  float threshold = 1 - profile;
  return max(noise - threshold, 0) * cloud_multiplier;
}
