#version 410 core

uniform float cloud_scale;

float cloud_octaves(vec3 idx, float lod);

float cloud_noise(vec3 point, float lod)
{
  return cloud_octaves(point / cloud_scale, lod);
}
