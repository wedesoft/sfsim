#version 410 core

uniform sampler3D worley;
uniform float cloud_scale;

float cloud_octaves(sampler3D noise, vec3 idx, float lod);

float cloud_noise(vec3 point, float lod)
{
  return cloud_octaves(worley, point / cloud_scale, lod);
}
