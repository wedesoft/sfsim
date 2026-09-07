#version 450 core

<% (when normals %>
in VS_OUT
{
  vec4 normal;
} fs_in;
<% ) %>

<% (when normals %>
layout (location = 0) out vec4 normals;
<% ) %>

void main()
{
<% (when normals %>
  normals = vec4(fs_in.normal.xyz, 1.0);
<% ) %>
}
