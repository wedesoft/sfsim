#version 450 core

// Convert 1D index to texture lookup coordinates with margin where the texture is clamped.
float convert_1d_index(float idx, int size)
{
  return (idx * (size - 1) + 0.5) / size;
}
