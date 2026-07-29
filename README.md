# MyLibrary

<p align="center">
  A minimalist Android app for recording books, movies, reading and watching experiences.
</p>

## Overview

MyLibrary is a local-first personal cultural archive app.

It focuses on:

- Recording books and movies
- Tracking reading and watching history
- Organizing works with tags and custom fields
- Saving quotes and personal notes
- Visualizing your cultural history through timeline, calendar and statistics

All data is stored locally. No account, server or online service is required.

---

## Screenshots

<!-- Add screenshots here -->

---

## Features

### Library Management

- Add, edit and delete books and movies
- Search and filter your collection
- Three library views:
  - Grid
  - List
  - Cover-only mode
- Soft delete and recycle bin

### Timeline & Calendar

- Timeline based on your recording history
- Monthly calendar with cover visualization
- Yearly overview
- Stable cover layout for different aspect ratios

### Records

Each work can contain multiple records:

- Date
- Status snapshot
- Rating
- Notes
- Duration
- Quotes

Records preserve historical information independently from the current work state.

### Tags & Fields

- Two-level tag system
- Custom fields:
  - Text
  - Number
  - Date
  - Single choice
  - Multiple choice
  - Rating

### Quotes

- Multiple quotes per work
- Page number and chapter information
- Search quotes by content, title and author

### Statistics

Statistics include:

- Book reading statistics
- Movie watching statistics
- Duration statistics
- Quote statistics
- Custom field statistics

### Backup & Restore

- Local backup export
- Restore from backup archive
- Database + cover image backup

---

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- Room Database
- DataStore
- Navigation Compose
- Kotlin Coroutines
- Kotlin Serialization

Architecture:
