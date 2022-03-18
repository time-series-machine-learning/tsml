%# This is a cut down version of the problem ChinaTown, useful for code examples and very slow unit tests
% The Train set is the same as ChinaTown, but the test set is reduced from 340 cases to 22 cases
%
@problemName UnitTest
@timeStamps false
@missing false
@univariate true
@equalLength true
@seriesLength 24
@classLabel true 1 2
@data
501.0,328.0,195.0,218.0,67.0,17.0,28.0,72.0,132.0,215.0,406.0,765.0,1207.0,1427.0,1234.0,1238.0,1107.0,1190.0,1255.0,1144.0,905.0,690.0,386.0,192.0:1
880.0,752.0,913.0,863.0,402.0,112.0,60.0,112.0,119.0,186.0,365.0,596.0,990.0,1193.0,1040.0,1063.0,1009.0,1025.0,1089.0,979.0,706.0,585.0,356.0,187.0:1
493.0,389.0,174.0,121.0,82.0,36.0,27.0,64.0,127.0,203.0,415.0,747.0,1164.0,1414.0,1520.0,1295.0,1265.0,1430.0,1637.0,1697.0,1456.0,1319.0,1179.0,848.0:1
616.0,323.0,162.0,166.0,68.0,26.0,34.0,68.0,123.0,263.0,815.0,1611.0,1823.0,2019.0,1763.0,1728.0,1568.0,1439.0,1431.0,1282.0,1078.0,857.0,498.0,248.0:1
389.0,276.0,161.0,124.0,35.0,26.0,51.0,75.0,71.0,126.0,225.0,496.0,968.0,1128.0,1117.0,993.0,819.0,879.0,998.0,1057.0,1014.0,987.0,836.0,680.0:1
548.0,384.0,245.0,147.0,101.0,40.0,30.0,66.0,77.0,209.0,380.0,650.0,1229.0,1527.0,1456.0,1333.0,1326.0,1293.0,1582.0,1713.0,1490.0,1270.0,1206.0,752.0:1
369.0,297.0,171.0,108.0,90.0,47.0,37.0,73.0,164.0,203.0,325.0,584.0,1160.0,1371.0,1238.0,1213.0,1268.0,1370.0,1530.0,1524.0,1343.0,1020.0,884.0,585.0:1
418.0,350.0,116.0,93.0,90.0,37.0,27.0,52.0,100.0,155.0,392.0,697.0,1093.0,1413.0,1353.0,1247.0,1280.0,1357.0,1520.0,1555.0,1212.0,1022.0,817.0,502.0:1
504.0,273.0,175.0,135.0,62.0,44.0,35.0,65.0,112.0,180.0,370.0,668.0,1205.0,1491.0,1390.0,1329.0,1342.0,1455.0,1653.0,1739.0,1537.0,1222.0,1041.0,665.0:1
498.0,339.0,170.0,194.0,94.0,48.0,31.0,60.0,164.0,166.0,355.0,777.0,1251.0,1358.0,1265.0,1280.0,1226.0,1379.0,1493.0,1561.0,1198.0,907.0,919.0,731.0:1
478.0,405.0,237.0,154.0,62.0,61.0,29.0,50.0,78.0,242.0,362.0,836.0,1462.0,1431.0,1459.0,1315.0,1214.0,1221.0,1299.0,1239.0,1208.0,966.0,653.0,296.0:1
595.0,300.0,190.0,137.0,44.0,34.0,22.0,56.0,100.0,186.0,405.0,702.0,1267.0,1459.0,1360.0,1179.0,1276.0,1350.0,1597.0,1609.0,1373.0,1051.0,897.0,592.0:1
129.0,91.0,47.0,15.0,46.0,21.0,38.0,90.0,203.0,226.0,316.0,453.0,850.0,1133.0,852.0,793.0,854.0,977.0,1135.0,1041.0,896.0,822.0,555.0,366.0:2
181.0,116.0,103.0,53.0,35.0,46.0,52.0,98.0,252.0,202.0,353.0,642.0,1181.0,1324.0,937.0,987.0,932.0,1242.0,1344.0,1327.0,1083.0,834.0,912.0,413.0:2
349.0,187.0,100.0,65.0,65.0,40.0,20.0,41.0,48.0,104.0,461.0,771.0,1449.0,1622.0,1389.0,1446.0,1240.0,1268.0,1320.0,1449.0,1438.0,1318.0,1075.0,484.0:2
219.0,143.0,120.0,27.0,15.0,23.0,36.0,88.0,223.0,212.0,317.0,487.0,1002.0,1063.0,718.0,773.0,759.0,989.0,1156.0,1211.0,1107.0,1094.0,879.0,582.0:2
124.0,51.0,37.0,12.0,15.0,23.0,31.0,89.0,188.0,316.0,442.0,656.0,1250.0,1427.0,1229.0,993.0,944.0,1056.0,1144.0,1068.0,855.0,681.0,411.0,245.0:2
540.0,484.0,252.0,160.0,158.0,46.0,49.0,53.0,141.0,167.0,459.0,806.0,1393.0,1638.0,1545.0,1441.0,1389.0,1495.0,1597.0,1822.0,1653.0,1261.0,876.0,679.0:2
103.0,78.0,34.0,19.0,29.0,24.0,26.0,67.0,168.0,185.0,315.0,670.0,1011.0,1089.0,957.0,766.0,790.0,946.0,1119.0,1163.0,1030.0,718.0,529.0,225.0:2
105.0,66.0,55.0,21.0,36.0,16.0,26.0,87.0,266.0,201.0,352.0,607.0,1095.0,1245.0,870.0,769.0,787.0,993.0,1138.0,936.0,712.0,532.0,312.0,160.0:2
95.0,63.0,45.0,26.0,3.0,23.0,28.0,108.0,209.0,241.0,372.0,549.0,1206.0,1223.0,1156.0,1102.0,1083.0,1107.0,1109.0,1193.0,900.0,660.0,442.0,226.0:2
193.0,195.0,90.0,46.0,49.0,15.0,21.0,45.0,106.0,147.0,365.0,767.0,1120.0,1125.0,1235.0,1056.0,1031.0,1006.0,1131.0,1034.0,856.0,587.0,317.0,224.0:2
