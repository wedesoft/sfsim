#version 410 core

float transmittance_forward(vec3 point, vec3 direction);

vec3 cloud_track(vec3 p, vec3 q, int n, vec3 light)
{
  float transmittance_p = transmittance_forward(p, vec3(0, 0, 0));
  float transmittance_q = transmittance_forward(q, vec3(0, 0, 0));
  return light * transmittance_p / transmittance_q;
}
