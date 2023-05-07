#version 410 core

uniform float cover_multiplier;
uniform float cloud_multiplier;
uniform float threshold;

float cloud_cover(vec3 point);
float sphere_noise(vec3 point);
float cloud_profile(vec3 point);

float cloud_base(vec3 point)
{
  float cover = cloud_cover(point) * cover_multiplier;
  float clouds = sphere_noise(point) * cloud_multiplier;
  float profile = cloud_profile(point);
  return (cover + clouds - threshold) * profile;
}
