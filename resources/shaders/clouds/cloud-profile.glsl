#version 450 core

uniform float radius;
uniform float cloud_bottom;
uniform float cloud_top;

float cloud_profile(vec3 point)
{
  float dist = (length(point) - radius - cloud_bottom) / (cloud_top - cloud_bottom);
  return min(min(mix(0.0, 1.0, dist * 8), mix(1.0, 0.4, dist * 1.6 - 0.2)), mix(0.4, 0.0, dist * 4 - 3));
}
