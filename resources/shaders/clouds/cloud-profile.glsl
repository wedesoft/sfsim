#version 410 core

uniform sampler1D profile;
uniform int profile_size;
uniform float radius;
uniform float cloud_bottom;
uniform float cloud_top;

float convert_1d_index(float idx, int size);

float cloud_profile(vec3 point)
{
  float dist = length(point);
  return texture(profile, convert_1d_index((dist - radius - cloud_bottom) / (cloud_top - cloud_bottom), profile_size)).r;
}
