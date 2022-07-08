#version 410 core

uniform float radius;
uniform float cloud_bottom;
uniform float cloud_top;

float cloud_density(vec3 point)
{
  return 1.0;
}
