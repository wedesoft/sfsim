#version 450 core

uniform float mach;

float shockfront(float radial_distance, float curvature_radius)
{
  float apex = curvature_radius * 0.143 * exp(3.24 / (mach * mach));
  return apex;
}
