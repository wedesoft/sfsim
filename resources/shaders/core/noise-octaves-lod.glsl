#version 410 core

float <%= base-function %>(vec3 idx, float lod);

float <%= method-name %>(vec3 idx, float lod)
{
  float result = 0.0;
<% (doseq [multiplier octaves] %>
  result += <%= multiplier %> * <%= base-function %>(idx, lod);
  idx *= 2;
  lod += 1;
<% ) %>
  return result;
}
