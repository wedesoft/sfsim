#version 410 core

// Convert cubemap index to avoid clamping regions.
vec3 convert_cubemap_index(vec3 idx, int size)
{
  vec3 scale;
  if (abs(idx.x) >= abs(idx.y)) {
    if (abs(idx.x) >= abs(idx.z))
      scale = vec3(size, size - 1, size - 1);
    else
      scale = vec3(size - 1, size - 1, size);
  } else {
    if (abs(idx.y) >= abs(idx.z))
      scale = vec3(size - 1, size, size - 1);
    else
      scale = vec3(size - 1, size - 1, size);
  };
  return idx * scale / size;
}
