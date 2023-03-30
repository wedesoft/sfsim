#version 410 core

float <%= base-function %>(vec3 idx);

float <%= method-name %>(vec3 idx)
{
  float result;
<% (doseq [multiplier octaves] %>
  result += <%= multiplier %> * <%= base-function %>(idx);
  idx *= 2;
<% ) %>
  return result;
}
