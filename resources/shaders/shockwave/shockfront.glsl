#version 450 core

uniform float mach;
uniform float object_radius;

float shockfront(float radial_distance, float curvature_radius)
{
  float mach2 = mach * mach;
  // 1. Standoff distance (Delta)
  float delta = curvature_radius * 0.143 * exp(3.24 / mach2);
  // 2. Shock radius of curvature at apex (Rc)
  float Rc = 1.143 * curvature_radius * exp(0.54 / pow(mach - 1.0, 1.2));
  // 3. Tangent of asymptotic Mach angle (tan_beta = 1 / sqrt(M^2 - 1))
  float tanb2 = 1.0 / (mach2 - 1.0);
  float cotb2 = 1.0 / tanb2;
  // 4. Hyperbolic profile term
  return delta - (sqrt(Rc * Rc + tanb2 * radial_distance * radial_distance) - Rc) * cotb2;
}
