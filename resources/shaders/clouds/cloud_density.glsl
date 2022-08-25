#version 410 core

uniform float radius;
uniform float cloud_bottom;
uniform float cloud_top;
uniform float cloud_scale;
uniform float cloud_multiplier;
uniform sampler3D worley;
uniform sampler1D cloud_profile;

float cloud_density(vec3 point, float lod)
{
  float dist = length(point);
  float noise = textureLod(worley, point / cloud_scale, lod).r;
  float threshold = 1 - texture(cloud_profile, (dist - radius - cloud_bottom) / (cloud_top - cloud_bottom)).r;
  return max(noise - threshold, 0) * cloud_multiplier;
}
