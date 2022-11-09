#version 410 core

float M_PI = 3.14159265358;

// Mie scattering phase function by Cornette and Shanks depending on assymetry g and mu = cos(theta)
float phase(float g, float mu)
{
  return 3 * (1 - g * g) * (1 + mu * mu) / (8 * M_PI * (2 + g * g) * pow(1 + g * g - 2 * g * mu, 1.5));
}
