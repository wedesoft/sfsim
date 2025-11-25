#version 450 core

uniform float detail_scale;

float cloud_octaves(vec3 idx, float lod);

float cloud_noise(vec3 point, float lod)
{
  return cloud_octaves(point / detail_scale, lod);
}
