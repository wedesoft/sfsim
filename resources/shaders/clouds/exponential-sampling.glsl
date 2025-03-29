#version 410 core

uniform float linear_range;
uniform float stepsize_factor;

float update_stepsize(float dist, float stepsize)
{
  if (dist < linear_range)
    return stepsize;
  else
    return stepsize * stepsize_factor;
}
