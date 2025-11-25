#version 450 core

uniform float nozzle;
uniform float omega_factor;

float plume_omega(float limit)
{
  float range = nozzle - limit;
  return omega_factor * range;
}

float plume_phase(float x, float limit)
{
  return plume_omega(limit) * x;
}
