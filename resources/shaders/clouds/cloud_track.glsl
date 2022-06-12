#version 410 core

float transmittance_forward(vec3 point, vec3 direction);

vec3 cloud_track(vec3 p, vec3 q, int n, vec3 light)
{
  float dist = distance(p, q);
  if (dist > 0) {
    vec3 direction = (q - p) / dist;
    float transmittance_p = transmittance_forward(p, direction);
    float transmittance_q = transmittance_forward(q, direction);
    return light * transmittance_p / transmittance_q;
  } else
    return light;
}
