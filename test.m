A=zeros(3,4);
B=ones(3,4);
for jj=1:3;
for ii=1:3;
A(ii)=B(ii)+A(ii);
end;
end;
ii=0;
if ii==0;
A(ii)=0;
end;
while ii<3
ii=ii+1;
end;
