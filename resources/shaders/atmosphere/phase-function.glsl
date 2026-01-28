#version 450 core

#define M_PI 3.1415926535897932384626433832795

// Mie scattering phase function by Cornette and Shanks depending on asymmetry g and mu = cos(theta)
float phase(float g, float mu)
{
  return 3 * (1 - g * g) * (1 + mu * mu) / (8 * M_PI * (2 + g * g) * pow(1 + g * g - 2 * g * mu, 1.5));
}
