#version 450 core

uniform float radius;
uniform float cloud_scale;

float <%= base-noise %>(vec3 point);

float sphere_noise(vec3 point)
{
  return <%= base-noise %>(point * (radius / (length(point) * cloud_scale)));
}
