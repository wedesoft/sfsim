#version 410 core

float <%= method-name %>(sampler3D noise, vec3 idx, float lod)
{
  float result = 0.0;
<% (doseq [multiplier octaves] %>
  result += <%= multiplier %> * textureLod(noise, idx, lod).r;
  idx *= 2;
<% ) %>
  return result;
}
