#version 410 core

uniform float cloud_scale;
uniform float cloud_multiplier;
uniform sampler3D worley;

float cloud_octaves(sampler3D noise, vec3 idx, float lod);
float cloud_profile(vec3 point);

float cloud_density(vec3 point, float lod)
{
  float noise = cloud_octaves(worley, point / cloud_scale, lod);
  float profile = cloud_profile(point);
  float threshold = 1 - profile;
  return max(noise - threshold, 0) * cloud_multiplier;
}
