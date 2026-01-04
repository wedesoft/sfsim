#version 450 core

uniform float plume_nozzle;
uniform float omega_factor;

float plume_omega(float limit)
{
  float range = plume_nozzle - limit;
  return omega_factor * range;
}

float plume_phase(float x, float limit)
{
  return plume_omega(limit) * x;
}
