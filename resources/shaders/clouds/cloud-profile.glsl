#version 410 core

uniform sampler1D profile;
uniform float radius;
uniform float cloud_bottom;
uniform float cloud_top;

float cloud_profile(vec3 point)
{
  float dist = length(point);
  return texture(profile, (dist - radius - cloud_bottom) / (cloud_top - cloud_bottom)).r;
}
