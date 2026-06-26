# FaceGate — Offline Face Recognition Attendance


---

## Tech Stack


---



```
    ↓
[1] ML Kit Face Detection
    ↓
[2] Face Count Check  →  0 faces: NoFace  /  2+ faces: MultipleFaces
    ↓
    ↓
    ↓
    ↓
    ↓
    ↓
    ↓
```


---


|---|---|---|

---


**`students`**
| Column | Type | Notes |
|---|---|---|
| studentId | TEXT PK | Roll number or unique ID |
| name | TEXT | Display name |

**`attendance_records`**
| Column | Type | Notes |
|---|---|---|
| timeStamp | INTEGER | Unix epoch ms |

**`conflict_queue`**
| Column | Type | Notes |
|---|---|---|


---



```bash
python3 scripts/convert_mobilefacenet_to_onnx.py
cp mobilefacenet.onnx app/src/main/assets/models/mobilefacenet.onnx
```

```bash
./gradlew assembleDebug
```

```


---

## Team - Interns of Reagvis Labs
Yash Krishan Gupta  
Mahima  
Mahi Garg  
Krish Bansal  
Pragati Dinkar Kharat  
Anmol Yadav
