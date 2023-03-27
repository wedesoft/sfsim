#version 410 core

uniform sampler3D <%= sampler %>;

float <%= method-name %>(vec3 idx, float lod)
{
  float result = 0.0;
<% (doseq [multiplier octaves] %>
  result += <%= multiplier %> * textureLod(<%= sampler %>, idx, lod).r;
  idx *= 2;
<% ) %>
  return result;
}
