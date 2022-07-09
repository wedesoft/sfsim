#version 410 core

uniform float radius;
uniform float cloud_bottom;
uniform float cloud_top;
uniform float cloud_size;
uniform float cloud_multiplier;
uniform sampler3D worley;
uniform sampler1D cloud_profile;

float cloud_density(vec3 point)
{
  float dist = length(point);
  if (dist >= radius + cloud_bottom && dist <= radius + cloud_top) {
    float noise = texture(worley, point / cloud_size).r;
    float threshold = 1 - texture(cloud_profile, (dist - radius - cloud_bottom) / (cloud_top - cloud_bottom)).r;
    return max(noise - threshold, 0) * cloud_multiplier;
  } else
    return 0.0;
}
