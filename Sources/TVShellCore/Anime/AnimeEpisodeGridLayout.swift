public enum AnimeEpisodeGridLayout {
    public static func rows(itemCount: Int, columns: Int) -> [[Int]] {
        let safeColumns = max(columns, 1)
        guard itemCount > 0 else { return [] }

        var rows: [[Int]] = []
        var current: [Int] = []
        for index in 0..<itemCount {
            current.append(index)
            if current.count == safeColumns {
                rows.append(current)
                current = []
            }
        }
        if current.isEmpty == false {
            rows.append(current)
        }
        return rows
    }
}
